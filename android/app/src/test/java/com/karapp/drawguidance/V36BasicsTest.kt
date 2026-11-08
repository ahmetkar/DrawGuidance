package com.karapp.drawguidance

import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.data.catalog.CatalogVectorRepository
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.Model3DResults
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CatalogVectorEntity
import com.karapp.drawguidance.ui.screens.account.LegalTexts
import com.karapp.drawguidance.ui.screens.model3d.Mode3
import com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel
import com.karapp.drawguidance.ui.screens.model3d.PaintScope
import com.karapp.drawguidance.ui.screens.settings.SettingsTab
import com.karapp.drawguidance.ui.theme.ThemePalette
import com.karapp.drawguidance.ui.theme.ThemePrefs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v36 (1, 6) Ayarlar: hesap başlığı en üstte (sekmelerin üzerinde, ekran genişliğinde), beş sekme (Genel, Çizim,
 * Kaydetme, Bağlantı, Gelişmiş) kendi kartlarını gösterir, dil açılır kutudan seçilir, renk grubu seçilince bütün
 * uygulama yeni renklere geçer ve seçim saklanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V36SettingsTest : V33EditorBase() {
    @After fun resetPalette() = ThemePrefs.select(ApplicationProvider.getApplicationContext(), ThemePalette.LAVENDER)

    @Test
    fun accountHeaderTabsLanguageAndPalette() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNode(hasContentDescription("Ayarlar").or(hasText("Ayarlar")) and hasAnyAncestor(hasTestTag("main_bottom_bar"))).performClick()
        waitFor { exists("settings_tabs") && exists("account_card") }
        // Hesap başlığı sekmelerin üstünde ve ekran genişliğinde
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("account_card").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("settings_tabs").fetchSemanticsNode().boundsInRoot
        assertTrue("başlık üstte", header.bottom <= tabs.top + 1f)
        assertTrue("başlık ekran genişliğinde: ${header.width} / ${root.width}", header.width >= root.width - 1f)
        assertEquals(5, SettingsTab.entries.size)
        SettingsTab.entries.forEach { assertTrue(it.name, exists("settings_tab_${it.name.lowercase()}")) }
        // Genel: dil açılır kutusu ve renk grupları; teknik kimlikler yok
        assertTrue(exists("language_select") && exists("theme_palette_card"))
        assertTrue(compose.onAllNodes(hasText("Firebase", substring = true)).fetchSemanticsNodes().isEmpty())
        shot("v36-settings-general")
        // Renk grubu: seçilince hemen uygulanır ve saklanır
        compose.onNodeWithTag("theme_palette_ocean").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { ThemePrefs.palette.value == ThemePalette.OCEAN }
        val prefs = compose.activity.getSharedPreferences("dg_theme", android.content.Context.MODE_PRIVATE)
        assertEquals("OCEAN", prefs.getString("palette", null))
        settleFor(200)
        shot("v36-settings-ocean")
        // Sekmeler kendi kartlarını gösterir
        val cards = mapOf(
            SettingsTab.DRAWING to "editor_settings_card", SettingsTab.SAVING to "export_settings_card",
            SettingsTab.CONNECTION to "sync_control_card", SettingsTab.ADVANCED to "reports_card",
        )
        for ((tab, card) in cards) {
            compose.onNodeWithTag("settings_tab_${tab.name.lowercase()}").performSemanticsAction(SemanticsActions.OnClick)
            waitFor { exists(card) }
            assertFalse(tab.name, exists("language_card"))
            cards.values.filter { it != card }.forEach { assertFalse("$tab: $it", exists(it)) }
        }
        compose.onNodeWithTag("settings_tab_general").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("language_card") }
    }
}

/** v36 (6) üç renk grubu: her biri açık ve koyu temada ayrı renkler, yazılar okunaklı (WCAG karşıtlığı). */
class V36ThemePaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    @Test
    fun threeReadablePalettes() {
        assertEquals(3, ThemePalette.entries.size)
        assertEquals(ThemePalette.LAVENDER, ThemePalette.of(null))
        assertEquals(ThemePalette.SUNSET, ThemePalette.of("SUNSET"))
        for (dark in listOf(false, true)) {
            val all = ThemePalette.entries.map { it.colors(dark) }
            assertEquals("ayrı zeminler", 3, all.map { it.background }.toSet().size)
            assertEquals("ayrı vurgular", 3, all.map { it.accent }.toSet().size)
            all.forEachIndexed { i, c ->
                val name = "${ThemePalette.entries[i]} koyu=$dark"
                assertEquals(name, dark, c.isDark)
                assertTrue("$name yazı ${contrast(c.background, c.content)}", contrast(c.background, c.content) >= 7f)
                assertTrue("$name ikincil yazı", contrast(c.background, c.contentMuted) >= 3f)
                assertTrue("$name vurgu", contrast(c.background, c.accent) >= 3f)
                assertTrue("$name vurgu üstü yazı", contrast(c.accent, c.onAccent) >= 4.5f)
            }
        }
    }
}

/** v36 (3) Kullanım Şartları ve KVKK Aydınlatma Metni: Türkçe ve İngilizce metinler, öteki dillerde İngilizce. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V36LegalTextsTest {
    @Test
    fun legalTextsInEveryLanguage() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (doc in LegalTexts.Doc.entries) {
            val tr = LegalTexts.load(ctx, doc, "tr")
            val en = LegalTexts.load(ctx, doc, "en")
            assertTrue(doc.name, tr.startsWith("# ") && tr.length > 1500)
            assertTrue(doc.name, en.startsWith("# ") && en.length > 1500)
            assertNotEquals(tr, en)
            assertTrue("sürüm metinde", tr.contains(LegalTexts.VERSION) && en.contains(LegalTexts.VERSION))
            assertEquals(en, LegalTexts.load(ctx, doc, "ja"))
        }
        assertTrue(LegalTexts.load(ctx, LegalTexts.Doc.KVKK, "tr").contains("6698"))
    }
}

/**
 * v36 (4, 5) 3B boyamada renk seçimi yalnız fırçayı değiştirir (son boyanan parçanın bütün yüzleri boyanmaz);
 * kütüphane şekilleri cihaz veritabanına sıkıştırılmış olarak yazılır ve aynen geri okunur.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V36DataTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    @Test
    fun paintColorOnlyChangesTheBrush() {
        val tex = TextureRepository(db.texture3DDao())
        val repo = Model3DRepository(context, db.model3DDao(), tex)
        val images = ImagesRepository(db.savedImageDao(), ImageExporter(context, useGallery = false), CanvasAssetStore(db.canvasAssetDao()))
        val vm = Model3DEditorViewModel(repo, tex, Model3DResults(), null, null, null, images = images)
        idle()
        vm.setViewport(900, 900)
        val cube = vm.model.value!!.parts[0]
        val base = cube.material
        vm.setMode(Mode3.PAINT)
        vm.setPaintScope(PaintScope.FACE)
        val red = Material(color = 0xFFE53935.toInt())
        vm.setPaint(red)
        vm.paintAt(0, 0)
        idle()
        // Boyanan yüz kırmızı, parça seçili; yeni renk seçilince parçanın öteki yüzleri değişmez
        assertEquals(red, vm.model.value!!.parts[0].materialOf(0))
        assertEquals(listOf(cube.id), vm.selection.value.parts)
        val blue = Material(color = 0xFF1E88E5.toInt())
        vm.setMaterial(blue)
        idle()
        assertEquals(blue, vm.paint.value)
        val part = vm.model.value!!.parts[0]
        assertEquals(base, part.material)
        assertEquals(setOf(0), part.faceMaterials.keys)
        assertEquals(red, part.materialOf(0))
        // Sonraki dokunuş yeni renkle yalnız o yüzü boyar
        vm.paintAt(0, 1)
        idle()
        val after = vm.model.value!!.parts[0]
        assertEquals(blue, after.materialOf(1))
        assertEquals(red, after.materialOf(0))
        assertEquals(setOf(0, 1), after.faceMaterials.keys)
    }

    @Test
    fun libraryShapesAreStoredCompressedAndReadBack() = runBlocking {
        var now = 1_000L
        val repo = CatalogVectorRepository(db.catalogVectorDao()) { now }
        val bundled = CatalogCodec.bundledVectors()
        // v43 nesne kütüphanesi kaldırıldı: karakter, efekt, fen ve genel şekiller kalır
        assertTrue("paketli vektör: ${bundled.size}", bundled.size > 100)
        assertTrue(repo.seedBundled())
        assertEquals(bundled.size, repo.count(CatalogVectorEntity.SOURCE_BUNDLED))
        // Aynı katalog sürümünde yeniden yazılmaz
        now = 2_000L
        assertFalse(repo.seedBundled())
        val rows = db.catalogVectorDao().bySource(CatalogVectorEntity.SOURCE_BUNDLED)
        assertTrue(rows.all { it.catalogVersion == CatalogInfo.VERSION && it.updatedAt == 1_000L })
        // İkili biçim + Deflate: yollar ham JSON'un altıda birinden küçük; geri okunan şekil aynı
        val raw = bundled.sumOf { CatalogCodec.encodePaths(it.paths).toByteArray(Charsets.UTF_8).size.toLong() }
        val packed = rows.sumOf { it.paths.size.toLong() }
        assertTrue("sıkıştırma $packed / $raw", packed * 6 < raw)
        val byId = rows.associateBy { it.id }
        for (v in bundled) {
            val back = CatalogVectorRepository.fromEntity(byId.getValue(v.id))!!
            assertEquals(v.copy(paths = emptyList()), back.copy(paths = emptyList()))
            assertEquals(v.id, CatalogCodec.encodePaths(v.paths), CatalogCodec.encodePaths(back.paths))
        }
        // Sunucudan gelen kayıtlar ayrı tutulur ve aynen okunur
        val remote = bundled.take(3).map { it.copy(title = it.title + " (sunucu)") }
        repo.saveRemote(remote, CatalogInfo.VERSION + 1)
        val read = repo.remoteVectors()
        assertEquals(remote.map { it.title }, read.sortedBy { r -> remote.indexOfFirst { it.id == r.id } }.map { it.title })
        // Aynı kimlikli yerleşik satır silinmez (anahtar kaynak + kimlik)
        assertEquals(bundled.size, repo.count(CatalogVectorEntity.SOURCE_BUNDLED))
        val id = remote.first().id
        assertEquals(bundled.first().title, db.catalogVectorDao().byId(CatalogVectorEntity.SOURCE_BUNDLED, id)!!.title)
        assertEquals(remote.first().title, db.catalogVectorDao().byId(CatalogVectorEntity.SOURCE_REMOTE, id)!!.title)
    }
}
