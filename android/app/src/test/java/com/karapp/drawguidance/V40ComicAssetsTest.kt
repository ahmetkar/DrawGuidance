package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.PremiumFeature
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicCatalog
import com.karapp.drawguidance.data.comicassets.ComicStyle
import com.karapp.drawguidance.data.comicassets.VariantGroup
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.premium.PremiumAccess
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.performTouchInput
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v40 hazır karakter ve arka plan kataloğu: dosyalar, adlar, boyut sınırı ve Premium ayrımı (JVM). */
class V40ComicCatalogTest {
    private val root = File("src/main/assets")
    private val catalog: ComicCatalog by lazy { ComicAssets.parse(File(root, ComicAssets.CATALOG).readText()) }
    private val langs = listOf("tr", "en", "ar", "ja", "ko", "ru", "zh")

    @Test
    fun catalogLoadsAndEveryListedAssetExists() {
        assertTrue("karakter sayısı: ${catalog.characters.size}", catalog.characters.size >= 40)
        assertTrue("arka plan sayısı: ${catalog.backgrounds.size}", catalog.backgrounds.size >= 30)
        val themes = catalog.themes.map { it.id }.toSet()
        val cats = catalog.bgCategories.map { it.id }.toSet()
        assertEquals(catalog.characters.size, catalog.characters.map { it.id }.toSet().size)
        for (c in catalog.characters) {
            assertTrue(c.id, c.theme in themes)
            assertTrue(c.id, c.style in ComicStyle.entries.map { it.id })
            assertTrue("${c.id} çeşitsiz", c.variants.isNotEmpty())
            assertEquals(c.id, c.variants.size, c.variants.map { it.id }.toSet().size)
            for (v in c.variants) {
                assertTrue(v.id, v.group in VariantGroup.entries.map { it.id })
                assertTrue(v.file, File(root, v.file).isFile)
                assertTrue(v.thumb, File(root, v.thumb).isFile)
                assertTrue(v.file, v.w in 16..640 && v.h in 16..640)
            }
        }
        for (b in catalog.backgrounds) {
            assertTrue(b.id, b.category in cats)
            assertTrue(b.file, File(root, b.file).isFile)
            assertTrue(b.thumb, File(root, b.thumb).isFile)
            assertTrue(b.file, maxOf(b.w, b.h) in 1280..1600)
        }
        // Her iki stil ve dört grup dolu; arka planlar iki sekmede
        for (s in ComicStyle.entries) assertTrue(s.id, catalog.characters.any { it.style == s.id })
        for (g in VariantGroup.entries) assertTrue(g.id, catalog.characters.any { c -> c.variants.any { it.group == g.id } })
        for (s in ComicStyle.entries) assertTrue(s.id, catalog.backgrounds.any { it.style == s.id })
        // Listede olmayan dosya kalmamış
        val listed = (catalog.characters.flatMap { c -> c.variants.flatMap { listOf(it.file, it.thumb) } } +
            catalog.backgrounds.flatMap { listOf(it.file, it.thumb) } +
            // v42 efekt ve nesne resimleri
            (catalog.effects + catalog.objects).flatMap { listOf(it.file, it.thumb) } + ComicAssets.CATALOG).toSet()
        val onDisk = File(root, "comic").walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path.replace('\\', '/') }.toSet()
        assertEquals(onDisk - listed, emptySet<String>())
    }

    @Test
    fun everyNameHasAllTranslations() {
        val all = catalog.themes.map { it.id to it.names } + catalog.bgCategories.map { it.id to it.names } +
            catalog.characters.map { it.id to it.names } + catalog.backgrounds.map { it.id to it.names }
        for ((id, names) in all) for (l in langs) assertTrue("$id/$l", !names[l].isNullOrBlank())
        // Seçili dil yoksa Türkçe
        val c = catalog.characters.first()
        assertEquals(c.names["tr"], ComicAssets.name(c.names - "en", "en"))
        assertEquals(c.names["ja"], ComicAssets.name(c.names, "ja"))
    }

    @Test
    fun assetSizeBudget() {
        val bytes = File(root, "comic").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // v42 efekt ve nesne resimleri (~14 MB) ve 20 yeni mekân eklendi
        assertTrue("çizgi roman varlıkları ${bytes / 1_000_000} MB > 55 MB", bytes <= 55L * 1024 * 1024)
    }

    @Test
    fun premiumSplit() {
        val free = PremiumAccess(Entitlements.FREE, CanvasKind.COMIC) {}
        val premium = PremiumAccess(Entitlements.premium(), CanvasKind.COMIC) {}
        val art = PremiumAccess(Entitlements.FREE, CanvasKind.ART) {}
        for (c in catalog.characters) {
            val f = PremiumRules.characterFeature(c)
            // v40 yalnız sahibin listesi Premium
            if (c.id in PremiumRules.PREMIUM_CHARACTERS) assertEquals(c.id, PremiumFeature.COMIC_CHARACTERS_ADVANCED, f)
            else assertNull(c.id, f)
            assertFalse(premium.locked(f))
        }
        assertTrue(catalog.characters.any { PremiumRules.characterFeature(it) == null })
        assertTrue(catalog.characters.any { free.locked(PremiumRules.characterFeature(it)) })
        for (b in catalog.backgrounds) {
            val f = PremiumRules.backgroundFeature(b)
            assertEquals(b.id, b.category !in setOf("sehir", "sokak", "doga_park") && b.id !in PremiumRules.FREE_BACKGROUNDS, free.locked(f))
            assertFalse(art.locked(f))
        }
        val freeShare = catalog.backgrounds.count { PremiumRules.backgroundFeature(it) == null }.toFloat() / catalog.backgrounds.size
        assertTrue("Free arka plan payı $freeShare", freeShare in 0.25f..0.6f)
        // Listedeki kimliklerin hepsi katalogda
        for (id in PremiumRules.PREMIUM_CHARACTERS) assertTrue(id, catalog.characters.any { it.id == id })
        // v46 sahibin zip'indeki 10 Premium anime karakteri eklendi
        assertEquals(26, PremiumRules.PREMIUM_CHARACTERS.size)
    }

    /** v40 sahibin listesindeki karakterler Manga'da; karakter adlarında kişi adı yok (meslek, yaş, rol). */
    @Test
    fun mangaListAndNoPersonNames() {
        val manga = listOf(
            "demirci_emi", "uzayli_buyucu", "ogrenci_ece", "zeynep", "fatma_nine", "buyucu_leyla", "kilicci_doruk",
            "elf_okcu_silvan", "samuray_aiko", "kilicci_lale",
        )
        for (id in manga) assertEquals(id, ComicStyle.MANGA.id, catalog.characters.single { it.id == id }.style)
        val people = listOf(
            "Aiko", "Ali", "Aras", "Arda", "Atlas", "Ayaz", "Ayşe", "Barış", "Bora", "Cem", "Cemal", "Cengiz", "Deniz", "Doruk",
            "Ece", "Efe", "Emi", "Emir", "Emre", "Fatma", "Hasan", "Kaan", "Kaya", "Kemal", "Kenji", "Lale", "Leyla", "Lunara",
            "Mert", "Murat", "Nova", "Nuri", "Osman", "Ozan", "Rıza", "Selin", "Silvan", "Tolga", "Tuğrul", "Volkan",
            "Kerem", "Ege", "Zeynep", "Can", "Elif", "Hilal",
        )
        for (c in catalog.characters) for ((lang, label) in c.names) {
            val words = label.split(Regex("[^\\p{L}]+")).toSet()
            for (p in people) assertFalse("${c.id}/$lang: $label", p in words)
        }
    }
}

/**
 * v40 Karakterler ve Arka plan pencereleri: stil/tema süzgeçleri, karakterin çeşit grupları, çeşidin resim elemanı
 * olarak eklenmesi, hazır arka planın sayfaya konması ve Free planda kilitler.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40ComicAssetsTest : V33EditorBase() {
    @org.junit.After fun resetAccount() { TestAccount.entitlements.value = Entitlements.premium() }

    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.COMIC) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun strokes(): List<Stroke> = vm().canvas.value?.strokes.orEmpty()
    private fun images() = strokes().filter { it.brushType == BrushType.IMAGE }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun count(): String = compose.onAllNodes(hasTestTag("char_count")).fetchSemanticsNodes().single()
        .config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun filtersVariantsAndInsert() {
        open("Karakter v40")
        val all = ComicAssets.catalog.characters
        addMenu("Karakterler")
        waitFor { exists("characters_sheet") }
        assertEquals("${all.size} karakter", count())
        click("char_style_manga")
        val manga = all.filter { it.style == ComicStyle.MANGA.id }
        waitFor { count() == "${manga.size} karakter" }
        val theme = manga.first().theme
        click("char_theme_$theme")
        waitFor { count() == "${manga.count { it.theme == theme }} karakter" }
        click("char_style_cizgi_roman")
        val comic = all.filter { it.style == ComicStyle.COMIC.id }
        waitFor { count() == "${comic.size} karakter" }
        // Free karakter: çeşit grupları görünür, dokununca resim elemanı eklenir
        val c = comic.first { PremiumRules.characterFeature(it) == null && it.variants(VariantGroup.EXPRESSION).isNotEmpty() && it.variants(VariantGroup.POSE).isNotEmpty() }
        click("char_theme_all")
        click("char_${c.id}")
        waitFor { exists("char_selected_name") }
        assertTrue(exists("char_group_ifade"))
        assertTrue(exists("char_group_poz"))
        val pose = c.variants(VariantGroup.POSE).first()
        click("charvar_${c.id}_${pose.id}")
        waitFor { images().size == 1 }
        val img = images().single()
        assertTrue(img.assetId != null)
        // Tam boy çeşit sayfanın kısa kenarının ~%55'i boyunda
        val h = img.points.maxOf { it.y } - img.points.minOf { it.y }
        assertEquals(550f, h, 30f)
        // Ekleme pencereyi kapatır; ifade (büst) için yeniden açılır ve daha küçük eklenir
        waitFor { !exists("characters_sheet") }
        addMenu("Karakterler")
        waitFor { exists("characters_sheet") }
        click("char_${c.id}")
        waitFor { exists("char_selected_name") }
        val expr = c.variants(VariantGroup.EXPRESSION).first()
        click("charvar_${c.id}_${expr.id}")
        waitFor { images().size == 2 }
        val h2 = images().last().let { s -> s.points.maxOf { it.y } - s.points.minOf { it.y } }
        assertEquals(320f, h2, 30f)
        addMenu("Karakterler")
        waitFor { exists("characters_sheet") }
        click("char_${c.id}")
        waitFor { exists("char_selected_name") }
        click("char_back")
        waitFor { exists("char_results") }
    }

    @Test
    fun freePlanPreviewsButLocksAdvancedCharactersAndBackgrounds() {
        TestAccount.entitlements.value = Entitlements.FREE
        open("Kilitli v40")
        val locked = ComicAssets.catalog.characters.first { PremiumRules.characterFeature(it) != null }
        addMenu("Karakterler")
        waitFor { exists("characters_sheet") }
        // Önizleme açık: kilitli karakterin çeşitleri görünür, eklemek "Premium gerekli" açar
        click("char_${locked.id}_locked")
        waitFor { exists("char_selected_name") }
        val v = locked.variants.first()
        click("charvar_${locked.id}_${v.id}_locked")
        waitFor { exists("premium_required_sheet") }
        compose.waitForIdle()
        assertTrue(images().isEmpty())
    }

    /** v41 hazır arka planlar tuval oluşturma ve Arka plan pencerelerinde yok; "Mekân arka planı ekle" penceresinde. */
    @Test
    fun locationSheetTabsCategoriesAndWholePage() {
        open("Arka plan v40")
        openBackground()
        assertFalse("Arka plan penceresinde hazır çizgi roman arka planı yok", exists("bg_comic_styles"))
        SystemBack.press()
        waitFor { !exists("background_sheet") }
        addMenu("Mekân arka planı")
        waitFor { exists("locations_sheet") }
        click("loc_style_manga")
        val manga = ComicAssets.catalog.backgrounds.filter { it.style == "manga" }
        waitFor { exists("loc_${manga.first().id}") }
        click("loc_style_cizgi_roman")
        val cat = ComicAssets.catalog.backgrounds.first { it.style == "cizgi_roman" && it.category == "sehir" }
        click("loc_cat_sehir")
        waitFor { exists("loc_${cat.id}") }
        assertFalse(exists("loc_${manga.first().id}"))
        shot("v41-04-location-sheet")
        // Şablon yokken bütün sayfayı kaplar; elemanların arkasında
        click("loc_${cat.id}")
        waitFor { !exists("locations_sheet") && images().size == 1 }
        val img = images().single()
        val xs = img.points.map { it.x }; val ys = img.points.map { it.y }
        assertEquals(0f, xs.min(), 1f); assertEquals(1000f, xs.max(), 1f)
        assertEquals(0f, ys.min(), 1f); assertEquals(1000f, ys.max(), 1f)
        assertEquals(img.id, strokes().first().id)
        // Tek geri alma adımı
        compose.runOnIdle { vm().undo() }
        waitFor { images().isEmpty() }
    }

    @Test
    fun locationDropsIntoPanelOrWholePage() {
        open("Mekân panel")
        compose.runOnIdle { vm().insertTemplate("two_rows") }
        waitFor { vm().comicPanelRects().size == 2 }
        val panels = vm().comicPanelRects()
        val (leaf, rect) = panels.entries.maxBy { it.value.top }
        addMenu("Mekân arka planı")
        waitFor { exists("locations_sheet") }
        val b = ComicAssets.catalog.backgrounds.first { it.style == "cizgi_roman" && PremiumRules.backgroundFeature(it) == null }
        click("loc_style_cizgi_roman")
        click("loc_${b.id}")
        waitFor { exists("loc_place_overlay") }
        shot("v41-05-location-place")
        // Alttaki panele bırak: arka plan o panelin sınırına oturur, panel çizgilerinin arkasında
        compose.onNodeWithTag("loc_place_overlay").performTouchInput {
            down(screenOf(rect.center.x, rect.top + 20f))
            moveTo(screenOf(rect.center.x, rect.center.y))
            up()
        }
        waitFor { !exists("loc_place_overlay") && images().size == 1 }
        val img = images().single()
        val xs = img.points.map { it.x }; val ys = img.points.map { it.y }
        assertEquals(rect.left, xs.min(), 1f); assertEquals(rect.right, xs.max(), 1f)
        assertEquals(rect.top, ys.min(), 1f); assertEquals(rect.bottom, ys.max(), 1f)
        val index = strokes().indexOfFirst { it.id == img.id }
        assertTrue("panellerin arkasında", strokes().drop(index + 1).count { PanelSets.isPanel(it) } == 2)
        assertTrue(leaf in panels)
        // Bütün sayfa düğmesi
        addMenu("Mekân arka planı")
        waitFor { exists("locations_sheet") }
        click("loc_${b.id}")
        waitFor { exists("loc_whole_page") }
        compose.onNodeWithTag("loc_whole_page").performClick()
        waitFor { images().size == 2 }
        val page = images().first()
        assertEquals(1000f, page.points.maxOf { it.x }, 1f)
        assertEquals(1000f, page.points.maxOf { it.y }, 1f)
        // Vazgeç: bir şey eklenmez
        addMenu("Mekân arka planı")
        waitFor { exists("locations_sheet") }
        click("loc_${b.id}")
        waitFor { exists("loc_cancel") }
        compose.onNodeWithTag("loc_cancel").performClick()
        waitFor { !exists("loc_place_overlay") }
        assertEquals(2, images().size)
    }

    /** v41 Free planda Premium mekân arka planı önizlenir, eklenmez. */
    @Test
    fun lockedLocationAsksForPremium() {
        TestAccount.entitlements.value = Entitlements.FREE
        open("Kilitli mekân")
        addMenu("Mekân arka planı")
        waitFor { exists("locations_sheet") }
        val locked = ComicAssets.catalog.backgrounds.first { it.style == "cizgi_roman" && PremiumRules.backgroundFeature(it) != null }
        click("loc_${locked.id}_locked")
        waitFor { exists("premium_required_sheet") }
        assertTrue(images().isEmpty())
    }
}
