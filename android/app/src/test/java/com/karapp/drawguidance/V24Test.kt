package com.karapp.drawguidance

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.Gltf3
import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Model3DCodec
import com.karapp.drawguidance.content.model3d.Model3DStore
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.Part
import com.karapp.drawguidance.content.model3d.Pick3
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.Primitives
import com.karapp.drawguidance.content.model3d.Samples3D
import com.karapp.drawguidance.content.model3d.Transform3
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.Model3DResults
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.Model3DEntity
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.state.Model3DController
import com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel
import com.karapp.drawguidance.ui.screens.model3d.OpKind
import com.karapp.drawguidance.ui.screens.model3d.Tool3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** v24 tuval testlerinin ortak yardımcıları. */
abstract class V24EditorBase : V23EditorBase() {
    /** v24: Arka plan Ekle'den kalktı; Düzenle modunun (ve Çiz'in) üst menüsünde. */
    protected fun openBackground() {
        // v34: Arka plan yalnız Çiz satırında
        val node = hasContentDescription("Arka plan") and hasClickAction() and (hasAnyAncestor(hasTestTag("tool_row_context")) or hasAnyAncestor(hasTestTag("tool_row_draw")))
        if (compose.onAllNodes(node).fetchSemanticsNodes().isEmpty()) mode("Çiz")
        waitFor { compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onNode(node).performScrollTo() }
        compose.onNode(node).performClick()
        waitFor { exists("background_sheet") }
    }
}

/**
 * v24 (2): arka plan Ekle menüsünde yok, Düzenle ve Çiz'in üst menüsünde. Not tuvalinde her sayfanın kendi arka planı
 * var: değişiklik açık sayfaya uygulanır, "Tüm sayfalara uygula" hepsine; yeni sayfa önceki sayfanınkini alır; tuval
 * yeniden açılınca korunur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V24PageBackgroundTest : V24EditorBase() {
    @Test
    fun perPageBackgroundAndApplyAll() {
        createNotesCanvasAndOpen("Sayfalar")
        // Ekle menüsünde Arka plan yok
        mode("Ekle")
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasContentDescription("Arka plan") and hasAnyAncestor(hasTestTag("tool_row_context"))).fetchSemanticsNodes().isEmpty())
        // İkinci sayfa
        compose.onNodeWithContentDescription("Sayfa ekle").performClick()
        waitFor { canvas().pageCount == 2 && canvas().currentPage == 1 }
        val firstBefore = canvas().backgroundOf(0).patternId
        openBackground()
        waitForText("sayfa 2 / 2", substring = true)
        shotAll("v24-01-page-background-sheet")
        compose.onNodeWithTag("bg_patterns").performScrollToNode(hasContentDescription("Kareli (1 cm)"))
        compose.onNodeWithContentDescription("Kareli (1 cm)").performClick()
        val chosen = "note.grid10"
        // v41 Uygula: yalnız bu sayfa
        compose.onNodeWithTag("bg_apply").performScrollTo().performClick()
        waitFor { !exists("background_sheet") }
        waitFor { canvas().pageBackground.patternId == chosen }
        assertEquals(chosen, canvas().backgroundOf(1).patternId)
        assertEquals(firstBefore, canvas().backgroundOf(0).patternId)
        // Tüm sayfalara uygula
        openBackground()
        compose.onNodeWithTag("bg_apply_all").performScrollTo().performClick()
        waitFor { !exists("background_sheet") }
        waitFor { canvas().backgroundOf(0).patternId == chosen }
        settleFor(300)
        shotAll("v24-02-applied-all")
        // Yeni sayfa önceki sayfanın arka planını alır
        compose.onNodeWithContentDescription("Sayfa ekle").performClick()
        waitFor { canvas().pageCount == 3 }
        assertEquals(chosen, canvas().pageBackground.patternId)
        // Kalıcı: veritabanından okununca sayfa arka planları aynı
        val repo = GlobalContext.get().get<CanvasRepository>()
        waitFor { runBlocking { repo.getCanvas(canvas().localId)?.backgroundOf(0)?.patternId == chosen && repo.getCanvas(canvas().localId)?.backgroundOf(2)?.patternId == chosen } }
        settleFor(300)
        shot("v24-03-page-background-canvas")
    }
}

/** v24 (1): not tuvalinde yazı kutusuna verilen arka plan, yazı eklerken açılan düzenleme alanında da görünür. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V24InlineBoxBackgroundTest : V24EditorBase() {
    @Test
    fun editorShowsBoxBackground() {
        createNotesCanvasAndOpen("Kutu")
        addText(screenOf(150f, 300f), "Kutulu not")
        waitFor { canvas().strokes.any { it.text != null } }
        clickElements()
        // v29 yazı kutusu tek yazı biçimi alanının "Kutu ve yer" bölümünde
        waitFor { exists("text_tab_BOX") }
        compose.onNodeWithTag("text_tab_BOX").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("text_box_controls") }
        compose.onNode(hasText("Resim") and hasAnyAncestor(hasTestTag("text_box_controls"))).performScrollTo()
        compose.onNode(hasText("Resim") and hasAnyAncestor(hasTestTag("text_box_controls"))).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { canvas().strokes.first { it.text != null }.text?.box?.imageId == "box.img.sticky_yellow" }
        compose.onNodeWithContentDescription("Paneli kapat").performClick()
        settleFor(400)
        // Yeni yazı: düzenleme alanı aynı kutu arka planıyla açılır
        addMenu("Yazı ekle")
        tap(screenOf(200f, 700f))
        waitFor { compose.onAllNodesWithTag("inline_text_editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("inline_text_editor"))).performTextInput("Yeni satır")
        settleFor(500)
        shot("v24-04-inline-editor-box")
        // Düzenleme alanının pikselleri (pencerenin çiziminden)
        val bounds = compose.onNodeWithTag("inline_text_editor").fetchSemanticsNode().boundsInWindow
        val root = compose.activity.window.decorView
        val full = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(full)) }
        val l = bounds.left.toInt().coerceIn(0, full.width - 1); val t = bounds.top.toInt().coerceIn(0, full.height - 1)
        val w = (bounds.width.toInt()).coerceIn(1, full.width - l); val h = (bounds.height.toInt()).coerceIn(1, full.height - t)
        val px = IntArray(w * h)
        full.getPixels(px, 0, w, l, t, w, h)
        // Sarı yapışkan not: kırmızı ve yeşil yüksek, mavi belirgin düşük
        val yellow = px.count { c -> val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF; r > 180 && g > 160 && b < r - 50 }
        assertTrue("düzenleme alanında kutu arka planı: $yellow / ${px.size}", yellow > px.size / 3)
    }
}

/** v24 (3): 3B düzenleyicide Nokta/Kenar aracı, kaydırıcı işlemleri, Ayarlar paneli ve hazır şekiller (ekranda). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V24Model3DEditorUiTest : V24EditorBase() {
    @Test
    fun edgeToolSettingsAndReadyShapes() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithText("3B Şekiller").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("models3d_screen") }
        // Hazır şekiller sekmesi
        compose.onNodeWithText("Hazır şekiller").performClick()
        waitFor { exists("model3d_catalog") }
        settleFor(2500)
        shot("v24-05-ready-shapes")
        compose.onNodeWithText("Fizik").performClick()
        settleFor(2000)
        shot("v24-06-ready-physics")
        compose.onNodeWithContentDescription("Eğik düzlem").performClick()
        waitFor { exists("model3d_editor") }
        settleFor(1500)
        shot("v24-07-editor-incline")
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor { exists("model3d_exit_dialog") || compose.onAllNodesWithText("Kaydetme").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kaydetme").performClick()
        waitFor { exists("models3d_screen") }

        // Yeni şekil: Kenar aracı
        compose.onNodeWithText("Şekillerim").performClick()
        compose.onNodeWithText("Yeni 3B şekil").performClick()
        waitFor { exists("model3d_editor") }
        settleFor(1200)
        tool3("Kenar")
        val vp = compose.onNodeWithTag("model3d_viewport").fetchSemanticsNode().size
        // Küpe dokun: önce parça seçilir, sonra kenar
        compose.onNodeWithTag("model3d_viewport").performTouchInput { click(Offset(vp.width * 0.5f, vp.height * 0.5f)) }
        settleFor(400)
        shot("v24-08-edge-tool")
        assertTrue(compose.onAllNodesWithText("Eğ").fetchSemanticsNodes().isNotEmpty())
        // Ayarlar paneli: bölümler ve başarım (v26 üst çubukta)
        compose.onNodeWithContentDescription("Ayarlar").performClick()
        waitFor { exists("model3d_panel_settings") }
        compose.onNodeWithText("Ekran kartıyla çiz (hızlı)").performScrollTo()
        assertTrue(compose.onAllNodesWithText("Görüntü kalitesi").fetchSemanticsNodes().isNotEmpty())
        settleFor(400)
        shot("v24-09-settings")
        compose.onNodeWithTag("model3d_panel_close").performClick()
        // Ekle: cisimler ve hazır şekiller
        mode3("Ekle")
        waitFor { exists("model3d_panel_add") }
        compose.onNodeWithTag("model3d_catalog").performScrollTo()
        settleFor(1500)
        shot("v24-10-add-panel-ready")
    }

}

/**
 * v24 (3): 3B düzenleyicinin ViewModel'i: dokunuşlar ışın atarak (Pick3) kenara/noktaya çevrilir; kenar eğme ve
 * yuvarlatma kaydırıcıyla önizlenip tek adımda uygulanır; nokta eklenir; sürüklenen taşıma ve nokta taşıma geçmişe tek
 * adım girer; hazır şekil eklenir.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V24Model3DViewModelTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    private fun vm(): Model3DEditorViewModel {
        val tex = TextureRepository(db.texture3DDao())
        val repo = Model3DRepository(ApplicationProvider.getApplicationContext(), db.model3DDao(), tex)
        val vm = Model3DEditorViewModel(repo, tex, Model3DResults(), null, null, null)
        idle()
        vm.setViewport(900, 900)
        return vm
    }

    /** Asıl parçanın görünür bir kenarının ekran ortası. */
    private fun visibleEdgePoint(vm: Model3DEditorViewModel): Offset {
        val m = vm.model.value!!
        val view = vm.currentView()!!
        val part = m.parts[0]
        val w = Pick3.worldVertices(part)
        val e = part.mesh.edges
        var i = 0
        while (i < e.size) {
            val a = e[i]; val b = e[i + 1]
            val mid = V3((w[3 * a] + w[3 * b]) / 2f, (w[3 * a + 1] + w[3 * b + 1]) / 2f, (w[3 * a + 2] + w[3 * b + 2]) / 2f)
            if (Pick3.visible(m, view, mid, 0)) {
                val s = view.project(mid)
                val hit = Pick3.edge(m, view, 0, s[0], s[1], 20f)
                if (hit != null && hit.second.visible) return Offset(s[0], s[1])
            }
            i += 4
        }
        error("görünür kenar yok")
    }

    @Test
    fun edgeSelectionBendRoundAndUndo() {
        val vm = vm()
        val m0 = vm.model.value!!
        assertFalse("Robolectric'te yazılım çizici", vm.gpu.value)
        vm.selectPart(m0.parts[0].id)
        vm.setTool(Tool3.EDGE)
        val p = visibleEdgePoint(vm)
        vm.tapAt(p.x, p.y, 30f)
        assertEquals(1, vm.selection.value.edges.size)
        // Eğ: önizleme (geçmişe girmez), Tamam ile tek adım
        vm.startOp(OpKind.BEND)
        assertNotNull(vm.op.value)
        vm.setOpAmount(0.4f)
        vm.setOpAmount(0.6f)
        assertFalse(vm.canUndo.value)
        vm.applyOp()
        val bent = vm.model.value!!.parts[0].mesh
        assertTrue("eğilen kenar bölündü: ${bent.vertexCount}", bent.vertexCount > 8)
        assertTrue(vm.canUndo.value)
        vm.undo()
        assertEquals(8, vm.model.value!!.parts[0].mesh.vertexCount)
        // Yuvarlat: küp kenarında yapılabilir
        vm.tapAt(p.x, p.y, 30f)
        assertEquals(1, vm.selection.value.edges.size)
        vm.startOp(OpKind.ROUND)
        vm.setOpAmount(0.3f)
        assertFalse("yuvarlatma yapılabildi", vm.op.value!!.failed)
        assertTrue(vm.model.value!!.parts[0].mesh.faceCount > 6)
        // Vazgeç: model başa döner
        vm.cancelOp()
        assertEquals(6, vm.model.value!!.parts[0].mesh.faceCount)
        assertNull(vm.op.value)
    }

    @Test
    fun addPointAndDragVerticesIsOneUndoStep() {
        val vm = vm()
        val id = vm.model.value!!.parts[0].id
        vm.selectPart(id)
        vm.setTool(Tool3.EDGE)
        val p = visibleEdgePoint(vm)
        assertTrue(vm.addPointAt(p.x, p.y, 30f))
        val mesh = vm.model.value!!.parts[0].mesh
        assertEquals(9, mesh.vertexCount)
        assertEquals(setOf(8), vm.selection.value.vertices)
        // Yeni noktayı sürükle: 10 ara adım, geçmişte tek adım
        val undoBefore = undoDepth(vm)
        val pt = vm.toScreen(Transform3.of(vm.model.value!!.parts[0]).apply(mesh.vertex(8)))!!
        assertTrue(vm.beginVertexDrag(pt.first, pt.second, 30f))
        repeat(10) { vm.dragVerticesBy(4f, -3f) }
        vm.endVertexDrag()
        val moved = vm.model.value!!.parts[0].mesh.vertex(8)
        assertTrue("nokta taşındı", (moved - mesh.vertex(8)).length > 0.02f)
        assertEquals(undoBefore + 1, undoDepth(vm))
        vm.undo()
        assertEquals(mesh.vertex(8), vm.model.value!!.parts[0].mesh.vertex(8))
    }

    @Test
    fun liveMoveAndCatalogAdd() {
        val vm = vm()
        val part = vm.model.value!!.parts[0]
        vm.selectPart(part.id)
        vm.setTool(Tool3.MOVE)
        vm.beginLive()
        repeat(15) { vm.moveSelected(V3(0.02f, 0f, 0f)) }
        vm.endLive()
        assertEquals(part.position.x + 0.3f, vm.model.value!!.parts[0].position.x, 1e-3f)
        vm.undo()
        assertEquals(part.position.x, vm.model.value!!.parts[0].position.x, 1e-5f)
        assertFalse(vm.canUndo.value)
        // Hazır şekil: parçaları yeni kimliklerle eklenir ve seçilir
        val before = vm.model.value!!.parts.size
        vm.addCatalog("geo.cube_diag")
        val after = vm.model.value!!
        val item = Catalog3D.byId("geo.cube_diag")!!.build()
        assertEquals(before + item.parts.size, after.parts.size)
        assertEquals(item.parts.size, vm.selection.value.parts.size)
        assertTrue(after.strokes.size >= item.strokes.size)
        // Ayrıntı sınırı: cisim dilimleri en çok 48
        vm.selectPart(after.parts[0].id)
        vm.setPrimitiveSpec(com.karapp.drawguidance.content.model3d.PrimitiveSpec(PrimitiveKind.SPHERE, segments = 200, rings = 200))
        assertTrue(vm.model.value!!.parts[0].primitive!!.segments <= Primitives.MAX_SEGMENTS)
    }

    private fun undoDepth(vm: Model3DEditorViewModel): Int {
        // Geri alma adımı sayısı: geri alınabildikçe say, sonra yinele
        var n = 0
        while (vm.canUndo.value) { vm.undo(); n++ }
        repeat(n) { vm.redo() }
        return n
    }
}

/**
 * v24 (4): 3B modeller GLB olarak saklanır (eski kayıtlar dönüştürülür), dışa aktarma dokuları gömer, başka programın
 * GLB'si ayrıntısı düşürülerek içe aktarılır; tuvaldeki 3B şeklin verisi GLB.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V24GlbDataTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun foreign(model: Model3D): ByteArray {
        val glb = Gltf3.write(model)
        val bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLen = bb.getInt(12)
        val root = Json.parseToJsonElement(String(glb, 20, jsonLen, Charsets.UTF_8)).jsonObject
        val asset = JsonObject(root["asset"]!!.jsonObject.filterKeys { it != "extras" })
        val json = JsonObject(root + ("asset" to asset)).toString().toByteArray()
        val pad = (4 - json.size % 4) % 4
        val bin = glb.copyOfRange(20 + jsonLen + 8, glb.size)
        val total = 12 + 8 + json.size + pad + 8 + bin.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(total)
        out.putInt(json.size + pad); out.putInt(0x4E4F534A); out.put(json); repeat(pad) { out.put(' '.code.toByte()) }
        out.putInt(bin.size); out.putInt(0x004E4942); out.put(bin)
        return out.array()
    }

    @Test
    fun storageImportExportAndMigration() = runBlocking {
        val tex = TextureRepository(db.texture3DDao())
        tex.seed()
        val repo = Model3DRepository(ApplicationProvider.getApplicationContext(), db.model3DDao(), tex)
        // Kayıt GLB
        val sample = Samples3D.all.first()
        repo.save(sample)
        val row = db.model3DDao().get(sample.id)!!
        assertTrue("GLB olarak saklandı", Gltf3.isGlb(row.data))
        assertEquals(sample.parts, repo.load(sample.id)!!.parts)

        // Eski (v23) kayıt açılışta GLB'ye çevrilir
        val legacy = Model3D("eski", "Eski model", listOf(ModelEdit.newPart(PrimitiveKind.CYLINDER, "c1", 0)))
        db.model3DDao().upsert(Model3DEntity("eski", "Eski model", Model3DCodec.encode(legacy), null, 1L, 2L, Model3DEntity.USER))
        assertNotNull(repo.load("eski"))
        repo.migrateToGlb()
        val migrated = db.model3DDao().get("eski")!!
        assertTrue(Gltf3.isGlb(migrated.data))
        assertEquals(2L, migrated.updatedAt)
        assertEquals(legacy.parts, Model3DStore.decode(migrated.data).parts)

        // Dışa aktarma: dokular gömülü (başka programlar için)
        val textured = sample.copy(parts = sample.parts.map { it.copy(material = Material(texture = "wood.oak")) })
        val exported = repo.exportGlb(textured)
        val jsonLen = ByteBuffer.wrap(exported).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val json = String(exported, 20, jsonLen, Charsets.UTF_8)
        assertTrue("gömülü resim", json.contains("\"images\"") && json.contains("image/png"))

        // Başka programın ayrıntılı modeli: üçgen sayısı düşürülür, boyut uygulamaya uyar
        val big = Part("b", "Küre", Primitives.sphere(64, 32), null, position = V3(0f, 50f, 0f), scale = V3(100f, 100f, 100f), smooth = true)
        val imported = repo.importGlb(foreign(Model3D("x", "x", listOf(big))), "Gezegen", Model3DRepository.DETAIL_LEVELS[0])
        assertTrue("kaynak ${imported.sourceTriangles}", imported.sourceTriangles > 3500)
        assertTrue("sonuç ${imported.triangles}", imported.triangles <= Model3DRepository.DETAIL_LEVELS[0] + 50)
        assertEquals("Gezegen", imported.model.name)
        repo.save(imported.model)
        assertTrue(repo.observe().first().any { it.id == imported.model.id })

        // Hazır şekil: yeni kimlikli kopya
        val c1 = repo.fromCatalog(Catalog3D.PREFIX + "chem.h2o")!!
        val c2 = repo.fromCatalog("chem.h2o")!!
        assertTrue(c1.id != c2.id && c1.parts.isNotEmpty())

        // Tuvaldeki 3B şeklin verisi GLB
        assertEquals("model/gltf-binary", Model3DController.MIME)
    }
}
