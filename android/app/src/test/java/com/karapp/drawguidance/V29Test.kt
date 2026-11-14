package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.shapes.MatchMode
import com.karapp.drawguidance.content.shapes.ShapeAugment
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeModeModels
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.model.BalloonSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.ModeTemplateSource
import com.karapp.drawguidance.data.repository.ShapeTemplateRepository
import com.karapp.drawguidance.data.transfer.CanvasTransfer
import com.karapp.drawguidance.data.transfer.TransferTarget
import com.karapp.drawguidance.ui.drawing.BalloonRenderer
import com.karapp.drawguidance.ui.drawing.DiagramCatalog
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings
import com.karapp.drawguidance.ui.screens.canvasedit.state.TextStyleMerge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v29 ortak yardımcılar: birden çok tuval, çizgi roman tuvali. */
abstract class V29EditorBase : V27EditorBase() {
    protected fun allCanvases(): List<CanvasUi> = runBlocking { GlobalContext.get().get<CanvasRepository>().observeCanvases().first() }

    protected fun createComicCanvasAndOpen(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasContentDescription("Yeni Tuval")).performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onAllNodesWithText("Çizgi roman")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Oluştur").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        prefs().setSelectionSuggest(false)
    }

    protected fun field() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor")))

    protected fun finishText() {
        compose.onNodeWithContentDescription("Yazıyı bitir").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Yazı ekle → dokunulan yerde yazılır. */
    protected fun addTextAt(at: Offset, content: String) {
        addMenu("Yazı ekle")
        waitForText("Yazı eklemek için tuvale dokun", substring = true)
        tap(at)
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        field().performTextInput(content)
        finishText()
    }

    protected fun texts(): List<Stroke> = canvas().strokes.filter { it.brushType == BrushType.TEXT }
}

/**
 * v29 (1) tek yazı biçimi alanı: Elemanlar panelinde yazının bütün ayarları tek bölümde (Yazı / Renk / Efekt / Kutu
 * ve yer), eski ayrı "Yazı Ayarları" ve "Yazı biçimi" kutuları yok; yazı düzenlenirken "Yazı biçimi" düğmesiyle renk,
 * kenarlık ve efektler de açılır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V29TextFormatTest : V29EditorBase() {
    @Test
    fun oneTextFormatSectionAndFormatInEditor() {
        createNotesCanvasAndOpen("Biçim")
        val size = canvasSize()
        addTextAt(Offset(size.x * 0.3f, size.y * 0.3f), "Merhaba")
        waitFor { texts().size == 1 }

        clickElements()
        waitFor { exists("text_format_section") }
        assertEquals("tek yazı biçimi bölümü", 1, compose.onAllNodesWithTag("text_format_section").fetchSemanticsNodes().size)
        assertTrue("eski ayrı yazı ayarları kutusu yok", compose.onAllNodesWithText("Yazı Ayarları").fetchSemanticsNodes().isEmpty())
        listOf("FONT", "COLOR", "EFFECTS", "BOX").forEach { assertTrue(it, exists("text_tab_$it")) }
        shotAll("v29-01-text-format-elements")
        // Renk bölümü: harf kenarlığı
        compose.onNodeWithTag("text_tab_COLOR").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("text_fill_border_section") }
        compose.onNodeWithText("Kenarlık rengi").performScrollTo().performClick()
        waitForText("Kenarlık var")
        compose.onNodeWithText("Kenarlık var").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { texts().single().text?.outlineColorHex != null }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()

        // Yazıya dokununca açılan düzenleyicide yazı biçimi (efektler)
        editButton("Metni Düzenle")
        waitFor { exists("inline_font_bar") }
        compose.onNode(hasContentDescription("Yazı biçimi") and hasAnyAncestor(hasTestTag("inline_font_bar"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("inline_format_panel") }
        // Yazı tipi ve boyut çubukta: panelde tekrar edilmez
        assertFalse(compose.onAllNodes(hasTestTag("text_tab_FONT") and hasAnyAncestor(hasTestTag("inline_format_panel"))).fetchSemanticsNodes().isNotEmpty())
        compose.onNode(hasTestTag("text_tab_EFFECTS") and hasAnyAncestor(hasTestTag("inline_format_panel"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("text_caps") }
        compose.onNodeWithTag("text_caps").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        shotAll("v29-02-text-format-inline")
        finishText()
        waitFor { texts().single().text?.box?.effects?.caps == true }
        assertNotNull("kenarlık korundu", texts().single().text?.outlineColorHex)
    }

    @Test
    fun onlyChangedFieldsMerge() {
        val target = TextSettings(com.karapp.drawguidance.data.model.TextSpec("", font = TextFont.COMIC_RELIEF, align = TextAlignKind.CENTER, bold = false), 60f)
        val before = TextSettings(com.karapp.drawguidance.data.model.TextSpec("", font = TextFont.SANS), 40f)
        val after = before.copy(spec = before.spec.copy(bold = true))
        val merged = TextStyleMerge.merge(target, before, after)
        assertTrue(merged.spec.bold)
        assertEquals(TextFont.COMIC_RELIEF, merged.spec.font)
        assertEquals(TextAlignKind.CENTER, merged.spec.align)
        assertEquals(60f, merged.sizePx)
    }
}

/**
 * v29 (2, 3) tuvale özgü son ayarlar ve çizgi romanda özel yazıların son ayar olmaması: balon ve efekt yazısı
 * eklendikten sonra yazılan yeni yazı balonun yazı tipini, ortalamasını ya da efekt yazısının büyük harfini almaz;
 * kullanıcının seçtiği ayar (kalın) son ayar olur. Bir tuvalin fırçası başka tuvale geçmez.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V29TextDefaultsTest : V29EditorBase() {
    @Test
    fun comicSpecialTextsDoNotBecomeLastSettings() {
        createComicCanvasAndOpen("Roman")
        val size = canvasSize()
        addMenu("Konuşma balonu")
        waitFor { chip("balloon_sheet") }
        compose.onNodeWithTag("balloon_SPEECH").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.any { it.text?.box?.balloon != null } }
        waitFor { exists("inline_text_editor") }
        finishText()
        val balloon = texts().single()
        assertEquals(TextAlignKind.CENTER, balloon.text!!.align)
        mode("Seç")
        addTextAt(Offset(size.x * 0.25f, size.y * 0.8f), "Normal")
        waitFor { texts().size == 2 }
        val plain = texts().first { it.text?.content == "Normal" }.text!!
        assertEquals("balonun yazı tipi geçmedi", TextFont.SANS, plain.font)
        assertEquals("balonun ortalaması geçmedi", TextAlignKind.LEFT, plain.align)
        assertNull("balon yok", plain.box?.balloon)

        // Kullanıcının seçtiği ayar (balon seçiliyken Kalın) son ayar olur; balonun diğer biçimi olmaz
        mode("Seç")
        val q = balloon.points
        tap(screenOf((q[0].x + q[2].x) / 2f, (q[0].y + q[2].y) / 2f))
        waitForText("1 eleman seçili")
        clickElements()
        waitFor { exists("text_format_section") }
        compose.onNode(hasContentDescription("Kalın") and hasAnyAncestor(hasTestTag("text_format_section"))).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { texts().first { it.text?.box?.balloon != null }.text!!.bold }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        compose.waitForIdle()
        // Efekt yazısı (büyük harf, kenarlık, gradyan) eklenir: yeni yazıya geçmez
        // v48 Efektler penceresinde ses yazıları bölümü yok: efekt yazısı doğrudan eklenir (eski belgelerdeki gibi)
        compose.runOnIdle { com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel.lastCreated!!.get()!!.insertSfx(com.karapp.drawguidance.content.comic.ComicSfx.presets[0]) }
        waitFor { texts().size == 3 }

        mode("Seç")
        addTextAt(Offset(size.x * 0.6f, size.y * 0.85f), "Kalın yazı")
        waitFor { texts().any { it.text?.content == "Kalın yazı" } }
        val bold = texts().first { it.text?.content == "Kalın yazı" }.text!!
        assertTrue("kalın son ayar oldu", bold.bold)
        assertFalse("efekt yazısının büyük harfi geçmedi", bold.box?.effects?.caps == true)
        assertNull("efekt yazısının kenarlığı geçmedi", bold.outlineColorHex)
        assertNull("efekt yazısının gradyanı geçmedi", bold.box?.effects?.gradient)
        assertEquals(TextFont.SANS, bold.font)
        assertEquals(TextAlignKind.LEFT, bold.align)
        // Tuvalin son yazı ayarı kalın (balonun yazı tipi değil)
        val saved = prefs().canvas(canvas().localId).lastText!!.first
        assertTrue(saved.bold)
        assertEquals(TextFont.SANS, saved.font)
    }

    @Test
    fun brushIsRememberedPerCanvas() {
        createCanvasAndOpen("A")
        pickBrush("Marker")
        val a = canvas().localId
        waitFor { prefs().canvas(a).lastBrush == BrushType.MARKER.name }
        SystemBack.press()
        compose.waitForIdle()
        waitFor { compose.onAllNodesWithContentDescription("Yeni Tuval").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
        compose.onNode(hasSetTextAction()).performTextInput("B")
        compose.onNodeWithText("Oluştur").clickInPopup()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        val b = allCanvases().first { it.name == "B" }.localId
        // Yeni tuval öntanımlı fırçayla açılır (A'nın Marker'ı geçmez)
        waitForDescription("Seçili fırça:", substring = true)
        assertTrue(compose.onAllNodesWithContentDescription("Seçili fırça: Marker").fetchSemanticsNodes().isEmpty())
        assertNull(prefs().canvas(b).lastBrush)
        assertEquals(BrushType.MARKER.name, prefs().canvas(a).lastBrush)
    }
}

/**
 * v29 (6, 8) yeni balon biçimleri ve balon boyutu; hız/odak çizgileri: yeni türler, eklerken sıklık ve eklenmiş
 * grubun sıklığının sonradan değiştirilmesi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V29ComicTest : V29EditorBase() {
    private fun slider(container: String) =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and hasAnyAncestor(hasTestTag(container)))

    @Test
    fun linesDensityAndBalloonSize() {
        createComicCanvasAndOpen("Çizgiler")
        // v36 odak çizgileri Efektler penceresinin ikinci sekmesinde
        addMenu("Efektler")
        waitFor { chip("effects_sheet") }
        compose.onNodeWithTag("effects_tab_lines").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { chip("lines_sheet") }
        // v50 çizgi roman ve manga bölümleri; her türün kendi ayarları (tür seçilir, ayarlanır, "Ekle")
        compose.onNodeWithTag("lines_style_comic").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(exists("lines_SWIRL") || runCatching { compose.onNodeWithTag("lines_SWIRL").performScrollTo(); true }.getOrDefault(false))
        compose.onNodeWithTag("lines_style_manga").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("lines_FOCUS").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        slider("lines_new_density").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.waitForIdle()
        shotAll("v29-03-lines-sheet")
        compose.onNodeWithTag("lines_add").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.size > 20 }
        val dense = canvas().strokes.size
        assertTrue(canvas().strokes.all { it.params?.comicLines?.density == 2f })
        // Eklenen grup seçili: seçimde "Çizgi sıklığı" ile sonradan değiştirilir
        finish()
        // Çizgilerden birine dokununca bütün grup seçilir
        val p = canvas().strokes[0].points.let { it[it.size / 2] }
        tap(screenOf(p.x, p.y))
        waitFor { compose.onAllNodesWithText("eleman seçili", substring = true).fetchSemanticsNodes().isNotEmpty() }
        waitFor { compose.onAllNodesWithContentDescription("Çizgi sıklığı").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Çizgi sıklığı").fetchSemanticsNodes().isNotEmpty() }
        val byDesc = compose.onAllNodesWithContentDescription("Çizgi sıklığı").fetchSemanticsNodes().isNotEmpty()
        if (byDesc) compose.onAllNodesWithContentDescription("Çizgi sıklığı")[0].performSemanticsAction(SemanticsActions.OnClick)
        else compose.onAllNodesWithText("Çizgi sıklığı")[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("lines_selected_density") }
        slider("lines_selected_density").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        waitFor { canvas().strokes.size < dense / 2 }
        val sparse = canvas().strokes
        assertTrue(sparse.all { it.params?.comicLines?.density == 0.5f })
        assertEquals("tek grup", 1, sparse.map { it.groupId }.distinct().size)
        // Tek geri alma adımı: önceki sıklık
        SystemBack.press()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Geri al").performScrollTo().performClick()
        waitFor { canvas().strokes.size == dense }
    }

    @Test
    fun newBalloonShapesRender() {
        val styles = BalloonStyle.entries
        assertTrue(styles.size >= 16)
        val dir = System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() }
        val cell = 260
        val bmp = Bitmap.createBitmap(cell * 4, cell * ((styles.size + 3) / 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        styles.forEachIndexed { i, style ->
            val b = BalloonSpec(style = style, tails = BalloonRenderer.defaultTails(1))
            val (body, _) = BalloonRenderer.outline(b, 200f, 110f)
            val r = android.graphics.RectF()
            body.computeBounds(r, true)
            assertTrue("$style biçimi var", r.width() > 100f && r.height() > 50f)
            val pads = BalloonRenderer.pads(b, 200f, 30f)
            assertTrue("$style iç boşluk", pads.all { it >= 0f } && pads.sum() > 0f)
            c.save(); c.translate((i % 4) * cell + 30f, (i / 4) * cell + 60f)
            BalloonRenderer.draw(c, b, 200f, 110f, 30f, 1f, coverage = false)
            c.restore()
        }
        if (dir != null) File(dir, "v29-04-balloons.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun transparentEditorialTheme() {
        val theme = DiagramThemes.find(DiagramThemes.EDITORIAL_CLEAR)
        assertNotNull(theme)
        assertTrue(DiagramThemes.isEditorial(DiagramThemes.EDITORIAL_CLEAR))
        fun render(themeId: String, bg: Int): Bitmap {
            val kind = DiagramKind.FLOW
            val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
            val spec = DiagramSpec(kind = kind, nodes = nodes, edges = DiagramRenderer.defaultEdges(kind, nodes), style = ShapeStyle(), theme = themeId,
                manual = DiagramCatalog.template(kind)?.manual ?: false)
            val s = Stroke("d", listOf(StrokePoint(20f, 20f), StrokePoint(620f, 20f), StrokePoint(620f, 520f), StrokePoint(20f, 520f)), "#1E1E1E", BrushType.DIAGRAM, 4f, 1f, 0, diagram = spec)
            val bmp = Bitmap.createBitmap(640, 540, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply { drawColor(bg); DiagramRenderer.draw(this, s) }
            return bmp
        }
        val bg = Color.rgb(120, 180, 230)
        val clear = render(DiagramThemes.EDITORIAL_CLEAR, bg)
        val paper = render(DiagramThemes.EDITORIAL, bg)
        // Saydam temada zemin görünür; kağıtlı temada kağıt rengi
        val pxClear = (0 until 640 * 540).count { clear.getPixel(it % 640, it / 640) == bg }
        val pxPaper = (0 until 640 * 540).count { paper.getPixel(it % 640, it / 640) == bg }
        println("zemin görünen piksel: saydam $pxClear, kağıtlı $pxPaper")
        assertTrue(pxClear > pxPaper + 100_000)
        // Çizgiler ve yazılar çizilir
        val ink = (0 until 640 * 540).count { val p = clear.getPixel(it % 640, it / 640); Color.red(p) < 80 && Color.green(p) < 80 && Color.blue(p) < 80 }
        assertTrue("mürekkep var: $ink", ink > 500)
        System.getenv("DG_SHOTS")?.takeIf { it.isNotBlank() }?.let { d -> File(d, "v29-05-editorial-clear.png").outputStream().use { clear.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}

/**
 * v29 (7) modlar arası aktarma ve iki seçenekli resim ekleme: resim tuvalindeki çizim yeni not tuvaline vektör olarak,
 * sonra aynı tuvale resim olarak aktarılır; not tuvalinde resim ekle → kayıtlı resimlerimden seç.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V29TransferTest : V29EditorBase() {
    private fun openTransfer() {
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitForText("Not / çizgi romana aktar")
        compose.onNodeWithTag("transfer_menu_item").performClick()
        waitFor { exists("transfer_sheet") }
    }

    @Test
    fun drawingGoesToNotesAsVectorAndImage() {
        createCanvasAndOpen("Kaynak")
        val c = canvas()
        drawStrokes(listOf(screenLine(c.width * 0.2f, c.height * 0.3f, c.width * 0.8f, c.height * 0.35f), screenCircle(c.width * 0.5f, c.height * 0.6f, c.width * 0.15f)))
        waitFor { canvas().strokes.size == 2 }
        val sourceId = canvas().localId
        openTransfer()
        shotAll("v29-06-transfer-sheet")
        compose.onNodeWithTag("transfer_new_NOTES").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor(15_000) { allCanvases().any { it.kind == CanvasKind.NOTES } }
        val notes = allCanvases().first { it.kind == CanvasKind.NOTES }
        waitFor(15_000) { runBlocking { GlobalContext.get().get<CanvasRepository>().getCanvas(notes.localId) }!!.strokes.size == 2 }
        val copied = runBlocking { GlobalContext.get().get<CanvasRepository>().getCanvas(notes.localId) }!!.strokes
        assertTrue("yeni kimlikler", copied.none { s -> canvasById(sourceId).strokes.any { it.id == s.id } })
        assertTrue(copied.all { it.brushType != BrushType.IMAGE })
        // Kaynak değişmedi
        assertEquals(2, canvasById(sourceId).strokes.size)

        // Resim olarak aynı not tuvaline
        waitFor { !exists("transfer_sheet") }
        openTransfer()
        compose.onNodeWithTag("transfer_image").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("transfer_target_${notes.localId}").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor(15_000) { canvasById(notes.localId).strokes.any { it.brushType == BrushType.IMAGE } }
        val image = canvasById(notes.localId).strokes.single { it.brushType == BrushType.IMAGE }
        assertNotNull(image.assetId)
        // Not sayfasının ortasında ve içinde
        val xs = image.points.map { it.x }; val ys = image.points.map { it.y }
        assertTrue(xs.min() >= 0f && xs.max() <= notes.width && ys.min() >= 0f && ys.max() <= notes.height)
    }

    private fun canvasById(id: String): CanvasUi = runBlocking { GlobalContext.get().get<CanvasRepository>().getCanvas(id) }!!

    @Test
    fun threeDOutlinesGoToComicAsOneGroup() = runBlocking {
        val transfer = GlobalContext.get().get<CanvasTransfer>()
        val lines = listOf(listOf(0f to 0f, 100f to 0f, 100f to 100f), listOf(0f to 0f, 0f to 100f))
        val strokes = lines.mapIndexed { i, l ->
            Stroke("o$i", l.map { (x, y) -> StrokePoint(x + 400f, y + 300f) }, "#1E1E1E", BrushType.INK_PEN, 3f, 1f, 0)
        }
        val result = transfer.sendStrokes(TransferTarget.New(CanvasKind.COMIC), "Küp", strokes, group = true)
        assertNotNull(result)
        val comic = GlobalContext.get().get<CanvasRepository>().getCanvas(result!!.canvasId)!!
        assertEquals(CanvasKind.COMIC, comic.kind)
        assertEquals(2, comic.strokes.size)
        assertEquals(1, comic.strokes.map { it.groupId }.distinct().size)
        assertNotNull(comic.strokes[0].groupId)
        // Sayfanın ortasına taşındı
        val cx = comic.strokes.flatMap { it.points }.let { p -> (p.minOf { it.x } + p.maxOf { it.x }) / 2f }
        assertEquals(comic.width / 2f, cx, 2f)
    }

    @Test
    fun notesImageAddOffersSavedImages() {
        createNotesCanvasAndOpen("Resimli not")
        val file = File(compose.activity.cacheDir, "kayitli.png").apply { writeBytes(ImageImportValidatorTest.png(ImageImportValidatorTest.simpleDrawing(600, 400))) }
        runBlocking {
            GlobalContext.get().get<AppDatabase>().savedImageDao().insert(
                SavedImageEntity(canvasLocalId = "x", title = "Kayıtlı resim", uri = android.net.Uri.fromFile(file).toString(), width = 600, height = 400,
                    createdAt = System.currentTimeMillis(), location = "Pictures/DrawGuidance/kayitli.png"),
            )
        }
        addMenu("Resim ekle")
        waitFor { exists("image_source_sheet") }
        assertTrue(exists("image_source_device"))
        compose.onNodeWithTag("image_source_saved").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("saved_image_grid") }
        shotAll("v29-07-image-source")
        compose.onNode(hasContentDescription("Kayıtlı resim")).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(15_000) { canvas().strokes.any { it.brushType == BrushType.IMAGE } }
    }
}

/**
 * v29 (5) moda özel yapay zekâ: çizgi roman tuvalinde aramalar yalnız çizgi roman kapsamında (balon şablonu tanınır,
 * ders şablonu çıkmaz), kabul edilen öneri hem veritabanına hem de yalnız çizgi roman modunun ağına öğretilir.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V29ModeAiTest {
    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun comicModeSearchAndOnDeviceTraining() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = ShapeTemplateRepository(db.shapeTemplateDao(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val dir = File(context.cacheDir, "modes-test").apply { deleteRecursively(); mkdirs() }
        val models = ShapeModeModels(dir)
        var mode: ShapeMode? = ShapeMode.COMIC
        val source = ModeTemplateSource(repo, models, { mode })
        val index = repo.load()
        assertTrue("moda özel şablonlar veritabanında", index.meta("roman.konusma") != null && index.meta("ders.venn") != null)
        // Model arka planda hazırlanır
        source.searchScope
        withTimeout(30_000) { while (!models.isReady(ShapeMode.COMIC)) delay(50) }
        val scope = source.searchScope!!
        assertNotNull(scope.net)
        val balloon = ShapeTemplates.byKey("roman.konusma")!!
        val drawn = ShapeAugment.handDrawn(balloon.strokes, java.util.Random(5))
        val res = index.search(drawn, MatchMode.SINGLE, 3, scope = scope)
        assertEquals("roman.konusma", res.first().key)
        assertTrue(res.none { it.key.startsWith("ders.") })
        // Genel aramada (mod yok) moda özel şablon çıkmaz
        assertTrue(index.search(drawn, MatchMode.SINGLE, 5).none { it.key.startsWith("roman.") })

        source.accepted("roman.konusma", drawn)
        withTimeout(10_000) { while (models.info(ShapeMode.COMIC, index).accepted < 1) delay(20) }
        withTimeout(10_000) { while (db.shapeTemplateDao().catalogCounts().first { it.key == "roman.konusma" }.usage < 1) delay(20) }
        assertTrue(File(dir, "shape_mode_comic.bin").exists())
        assertEquals("not modu etkilenmedi", 0, models.info(ShapeMode.NOTES, index).accepted)
        // Not modunda kapsam farklı
        mode = ShapeMode.NOTES
        source.searchScope
        withTimeout(30_000) { while (!models.isReady(ShapeMode.NOTES)) delay(50) }
        val notes = source.searchScope!!
        assertTrue("ders.eksen" in notes.keys && "roman.konusma" !in notes.keys)
        db.close()
    }
}
