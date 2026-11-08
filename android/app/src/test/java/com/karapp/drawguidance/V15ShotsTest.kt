package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.data.local.FolderDao
import com.karapp.drawguidance.data.local.FolderEntity
import com.karapp.drawguidance.data.local.FolderKind
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.FormulaRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.sin

/** v15 görsel kontrolü (DG_SHOTS verilince ekran görüntüsü): formül, desen, diyagram temaları, grafikler, katmanlar, listeler. */
abstract class V15ShotsBase : WideLayoutTestBase() {

    protected fun settle() {
        repeat(6) { Thread.sleep(120); compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
    }

    private val style = ShapeStyle(strokeColorHex = "#263238", strokeWidth = 3f)

    private fun quad(l: Float, t: Float, r: Float, b: Float) = listOf(StrokePoint(l, t), StrokePoint(r, t), StrokePoint(r, b), StrokePoint(l, b))

    protected fun formula(id: String, source: String, size: Float, x: Float, y: Float, order: Int) = Stroke(
        id, FormulaRenderer.quadAt(source, size, x, y).map { StrokePoint(it.x, it.y) }, "#1A237E", BrushType.FORMULA, size, 1f, order,
        formula = FormulaSpec(source),
    )

    protected fun diagram(id: String, kind: DiagramKind, theme: String, l: Float, t: Float, r: Float, b: Float, order: Int): Stroke {
        val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
        val spec = DiagramSpec(kind = kind, nodes = nodes, edges = DiagramRenderer.defaultEdges(kind, nodes), style = style, theme = theme)
        return Stroke(id, quad(l, t, r, b), style.strokeColorHex, BrushType.DIAGRAM, 3f, 1f, order, diagram = spec)
    }

    protected fun shape(id: String, kind: String, l: Float, t: Float, r: Float, b: Float, order: Int, st: ShapeStyle = style): Stroke =
        ShapeFactory.build(kind, Offset(l, t), Offset(r, b), st, id = id, order = order)!!

    protected fun ink(id: String, type: BrushType, color: String, y: Float, x0: Float, x1: Float, size: Float, order: Int) = Stroke(
        id, (0..40).map { i -> val x = x0 + (x1 - x0) * i / 40f; StrokePoint(x, y + sin(i / 40f * 6.28f) * 30f) }, color, type, size, 1f, order,
    )

    /** Açılışta kağıt görünüme sığdırılır (24 dp pay): tuval noktasının ekran karşılığı. */
    protected fun fit(view: Offset, cw: Float, ch: Float, x: Float, y: Float): Offset {
        val margin = 24f * compose.density.density
        val scale = minOf((view.x - 2 * margin) / cw, (view.y - 2 * margin) / ch)
        val ox = (view.x - cw * scale) / 2f
        val oy = (view.y - ch * scale) / 2f
        return Offset(ox + x * scale, oy + y * scale)
    }

    protected fun openCanvas(name: String) {
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { compose.onAllNodesWithTag("drawing_canvas").fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    protected fun notesLesson(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("Ders: Fizik ve Matematik", 1240, 1754, "#FFFFFF", false, kind = CanvasKind.NOTES)
            val strokes = listOf(
                formula("f1", "x_{1,2} = \\frac{-\\slot{b}{b} \\pm \\sqrt{\\slot{b}{b}^2 - 4\\slot{a}{a}\\slot{c}{c}}}{2\\slot{a}{a}}", 64f, 80f, 60f, 0),
                formula("f2", "\\int_{\\slot{a}{0}}^{\\slot{b}{1}} x^{2}\\,dx = \\frac{1}{3}", 56f, 760f, 80f, 1),
                shape("g1", "curve.sinus", 80f, 300f, 600f, 640f, 2),
                diagram("d1", DiagramKind.VENN, "pastel", 660f, 300f, 1160f, 640f, 3),
                shape("s1", "lib.sci.phys.prism", 80f, 700f, 560f, 1000f, 4),
                shape("s2", "lib.sci.bio.animal_cell", 660f, 700f, 1160f, 1010f, 5),
                diagram("d2", DiagramKind.PYRAMID, "vivid", 80f, 1060f, 560f, 1420f, 6),
                diagram("d3", DiagramKind.MIND_MAP, "ocean", 640f, 1060f, 1180f, 1420f, 7),
            )
            repository.saveStrokes(c.localId, listOf(strokes))
        }
        waitForText("Ders: Fizik ve Matematik")
        openCanvas("Ders: Fizik ve Matematik")
        shot("$prefix-1-lesson")
        // Formül seçilince düzenlenebilir kısımlar vurgulanır
        mode("Seç")
        val size = canvasSize()
        tap(fit(size, 1240f, 1754f, 330f, 150f))
        settle()
        shot("$prefix-2-formula-selected")
        runCatching {
            // v16: formül formülün üstünde düzenlenir
            compose.onNodeWithText("Formülü düzenle").performClick()
            waitFor { compose.onAllNodesWithTag("formula_inline_editor").fetchSemanticsNodes().isNotEmpty() }
            settle()
            shot("$prefix-3-formula-edit")
            compose.onNodeWithContentDescription("Formülü bitir").performClick()
        }
        compose.waitForIdle()
        // Katmanlar paneli
        compose.onNodeWithContentDescription("Katmanlar").performScrollTo().performClick()
        waitFor { compose.onAllNodesWithTag("layers_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Yeni katman").performClick()
        settle()
        shot("$prefix-4-layers")
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        // Ekle menüsü (son seçilen etkin)
        addMenu("Formül ekle")
        waitFor { compose.onAllNodesWithTag("formula_picker").fetchSemanticsNodes().isNotEmpty() }
        settle()
        shot("$prefix-5-formula-picker")
        runCatching { compose.onNodeWithText("Türev ve integral").performClick() }
        settle()
        shot("$prefix-6-formula-picker-calculus")
        SystemBack.press()
        compose.waitForIdle()
        // v18: eklenecekler Ekle modunun üst menüsünde; son seçilen işaretli
        mode("Ekle")
        waitForText("Eklemek istediğin yere dokun")
        settle()
        shot("$prefix-7-add-menu-active")
        addMenu("Diyagram ekle")
        waitForText("Tema")
        settle()
        shot("$prefix-9-diagram-picker")
        SystemBack.press()
        compose.waitForIdle()
        addMenu("Şekil ekle")
        waitForText("Grafik")
        compose.onAllNodesWithText("Grafik")[0].performClick()
        settle()
        shot("$prefix-10-graph-section")
        compose.onAllNodesWithText("Fen")[0].performClick()
        settle()
        shot("$prefix-11-science-section")
    }

    protected fun shapeEdit(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("Grafik Dersi", 1240, 1754, "#FFFFFF", false, kind = CanvasKind.NOTES)
            repository.saveStrokes(c.localId, listOf(listOf(shape("g", "graph.BAR", 140f, 200f, 1100f, 900f, 0), shape("h", "HEXAGON", 300f, 1000f, 900f, 1560f, 1))))
        }
        waitForText("Grafik Dersi")
        openCanvas("Grafik Dersi")
        mode("Seç")
        val size = canvasSize()
        tap(Offset(size.x * 0.5f, size.y * 0.33f))
        editButton("Şekli düzenle")
        waitForText("Şekli Düzenle: Grafik")
        compose.onNodeWithContentDescription("Deseni artır").performClick()
        settle()
        shot("$prefix-1-graph-tool")
        // v16: iş bitince İncele moduna geçilir
        compose.onNodeWithContentDescription("Bitti").performClick()
        settle()
        mode("Seç")
        // Altıgenin kenarı (tuval → ekran: kağıt görünüme sığdırılmış)
        tap(fit(size, 1240f, 1754f, 300f + 600f * 0.25f, 1000f + 560f * 0.07f))
        editButton("Şekli düzenle")
        waitForText("Şekli Düzenle: Noktalar")
        settle()
        shot("$prefix-2-points")
        compose.onNodeWithContentDescription("Parça özellikleri").performClick()
        waitFor { compose.onAllNodesWithTag("part_style_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Noktalı").performClick()
        settle()
        shot("$prefix-3-part-style")
    }

    protected fun brushes(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            val c = repository.createCanvas("Fırçalar", 1080, 1080, "#FFFFFF", false)
            repository.saveStrokes(
                c.localId,
                listOf(
                    listOf(
                        ink("a", BrushType.DOT_PEN, "#E53935", 200f, 100f, 980f, 14f, 0),
                        ink("b", BrushType.BRUSH_3D, "#1E88E5", 420f, 100f, 980f, 30f, 1),
                        ink("c", BrushType.BRUSH_3D, "#43A047", 620f, 100f, 980f, 18f, 2),
                        shape("d", "lib.draw.lighthouse", 120f, 700f, 420f, 1060f, 3),
                        shape("e", "lib.draw.owl", 520f, 740f, 800f, 1060f, 4),
                    ),
                ),
            )
        }
        waitForText("Fırçalar")
        openCanvas("Fırçalar")
        shot("$prefix-1-brushes")
        compose.onNodeWithContentDescription("Fırça Seç").performScrollTo().performClick()
        waitForText("Nokta İzi")
        settle()
        shot("$prefix-2-picker")
    }

    protected fun lists(prefix: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        val folderDao = GlobalContext.get().get<FolderDao>()
        runBlocking {
            folderDao.upsert(FolderEntity("cf", "Dersler", FolderKind.CANVAS.name, 1L))
            repository.createCanvas("Fizik", 1240, 1754, "#FFFFFF", false, kind = CanvasKind.NOTES, pageCount = 3, folderId = "cf")
            val c = repository.createCanvas("Manzara", 1600, 1000, "#FFFFFF", false)
            repository.saveStrokes(c.localId, listOf(listOf(ink("a", BrushType.MARKER, "#1E88E5", 500f, 100f, 1500f, 30f, 0))))
            repository.createCanvas("Portre", 1080, 1920, "#F5F0E6", false)
            folderDao.upsert(FolderEntity("if", "Fizik", FolderKind.IMAGE.name, 1L, "c"))
            val dao = GlobalContext.get().get<SavedImageDao>()
            dao.insert(SavedImageEntity(canvasLocalId = "c", title = "Fizik_s1", uri = "file:///yok.png", width = 1240, height = 1754, createdAt = 1L, location = "x", sizeBytes = 120_000, folderId = "if"))
            dao.insert(SavedImageEntity(canvasLocalId = "d", title = "Manzara", uri = "file:///yok2.png", width = 1600, height = 1000, createdAt = 2L, location = "x", sizeBytes = 240_000))
        }
        waitForText("Manzara")
        settle()
        shot("$prefix-1-canvas-folders")
        compose.onAllNodesWithText("Manzara")[0].performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Klasör Dersler").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        shot("$prefix-2-canvas-multiselect")
        SystemBack.press()
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Klasörler")
        compose.onAllNodesWithText("Manzara")[0].performTouchInput { longClick() }
        settle()
        shot("$prefix-3-images-select")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15LessonShotsTest : V15ShotsBase() {
    @Test fun lesson() = notesLesson("v15-lesson")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15ShapeEditShotsTest : V15ShotsBase() {
    @Test fun edit() = shapeEdit("v15-edit")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15BrushShotsTest : V15ShotsBase() {
    @Test fun brushes() = brushes("v15-brushes")
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15ListShotsTest : V15ShotsBase() {
    @Test fun lists() = lists("v15-lists")
}
