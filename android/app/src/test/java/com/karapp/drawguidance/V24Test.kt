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
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.Model3DEntity
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.state.Model3DController
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
        // v51 not alma basit menüde: Arka plan Ekle menüsünde
        if (simpleRow() && clickSimple("Arka plan")) { waitFor { exists("background_sheet") }; return }
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
        // v56 kayıt D3 v2
        val sample = Samples3D.all.first()
        repo.save(sample)
        val row = db.model3DDao().get(sample.id)!!
        assertFalse("D3 v2 olarak saklandı", Model3DStore.isLegacy(row.data))
        assertEquals(sample.parts, repo.load(sample.id)!!.parts)

        // v56 eski GLB kaydı açılışta D3 v2'ye çevrilir; elle düzenlenmiş parça kilitli cisim olur
        val cyl = ModelEdit.newPart(PrimitiveKind.CYLINDER, "c1", 0)
        val legacy = Model3D("eski", "Eski model", listOf(cyl, cyl.copy(id = "c2", primitive = null)))
        db.model3DDao().upsert(Model3DEntity("eski", "Eski model", Gltf3.write(legacy), null, 1L, 2L, Model3DEntity.USER))
        assertTrue(repo.load("eski")!!.parts.first { it.id == "c2" }.locked)
        repo.migrateToSketch()
        val migrated = db.model3DDao().get("eski")!!
        assertFalse(Model3DStore.isLegacy(migrated.data))
        assertEquals(2L, migrated.updatedAt)
        val back = Model3DStore.decode(migrated.data)
        assertFalse(back.parts.first { it.id == "c1" }.locked)
        assertTrue(back.parts.first { it.id == "c2" }.locked)
        // Asıl GLB yedekte
        assertTrue(java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "models3d-backup/eski.glb").exists())

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
